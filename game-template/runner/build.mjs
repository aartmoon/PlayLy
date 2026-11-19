import { spawnSync } from 'node:child_process'
import { createServer } from 'node:http'
import { readFile, readdir, mkdir, writeFile, copyFile, symlink } from 'node:fs/promises'
import { dirname, extname, join, resolve, sep } from 'node:path'
import { createRequire } from 'node:module'
import ts from 'typescript'

const { chromium, devices } = createRequire('/browser/package.json')('playwright')
const work = '/work'
const dist = join(work, 'dist')
const mime = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
])
const maxArtifactSize = 16 * 1024 * 1024

class RejectedGame extends Error {
  constructor(code, message) {
    super(message)
    this.code = code
  }
}

function verifyImports(files) {
  const allowedPaths = new Set(files.map(file => resolve(work, file.path)))
  for (const { path, content } of files) {
    const source = ts.createSourceFile(path, content, ts.ScriptTarget.ES2020, true)
    const reject = message => { throw new RejectedGame(20, path + ': ' + message) }
    const base = dirname(resolve(work, path))
    if (source.referencedFiles.length || source.typeReferenceDirectives.length || source.libReferenceDirectives.length) {
      reject('Triple-slash references are disabled.')
    }
    function visit(node) {
      if (ts.isImportEqualsDeclaration(node) || (ts.isMetaProperty(node) && node.keywordToken === ts.SyntaxKind.ImportKeyword)) {
        reject('Use standard static imports.')
      }
      if (ts.isCallExpression(node) && (node.expression.kind === ts.SyntaxKind.ImportKeyword
          || (ts.isIdentifier(node.expression) && node.expression.text === 'require'))) {
        reject('Use static imports only.')
      }
      if (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) {
        const specifier = node.moduleSpecifier?.text
        if (specifier && specifier !== 'phaser') {
          const target = resolve(base, specifier)
          if (!/^\.\.?\//.test(specifier) || !target.startsWith(join(work, 'src') + sep)
              || ![target, target + '.ts', join(target, 'index.ts')].some(file => allowedPaths.has(file))) {
            reject('Import must resolve to Phaser or a generated file: ' + specifier)
          }
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(source)
  }
}

function runTool(name, tool, args) {
  const result = spawnSync(process.execPath, [tool, ...args], {
    cwd: work, encoding: 'utf8', timeout: 60_000, maxBuffer: 1024 * 1024,
  })
  if (result.error?.code === 'ETIMEDOUT') {
    throw new Error(name + ' exceeded its 60-second time limit.')
  }
  if (result.error?.code === 'ENOBUFS') {
    throw new RejectedGame(20, name + ' exceeded its output limit.')
  }
  if (result.error) throw result.error
  if (result.status !== 0) {
    throw new RejectedGame(20, (result.stderr + '\n' + result.stdout).trim().slice(0, 6000))
  }
}

async function build(files) {
  verifyImports(files)
  for (const name of ['index.html', 'shell.css', 'tsconfig.json', 'vite.config.ts', 'package.json']) {
    await copyFile(join('/runner', name), join(work, name))
  }
  await symlink('/runner/node_modules', join(work, 'node_modules'), 'dir')
  for (const { path, content } of files) {
    const target = resolve(work, path)
    if (!target.startsWith(join(work, 'src') + sep) || !target.endsWith('.ts')) {
      throw new Error('Invalid runner input path.')
    }
    await mkdir(dirname(target), { recursive: true })
    await writeFile(target, content, { flag: 'wx' })
  }
  runTool('TypeScript', '/runner/node_modules/typescript/bin/tsc', ['--noEmit'])
  runTool('Vite', '/runner/node_modules/vite/bin/vite.js', ['build', '--configLoader', 'runner'])
}

async function testGame() {
  const server = createServer(async (request, response) => {
    try {
      const { pathname } = new URL(request.url, 'http://localhost')
      const file = resolve(dist, '.' + (pathname === '/' ? '/index.html' : pathname))
      if (!file.startsWith(dist + sep)) throw new Error('Invalid path')
      const body = await readFile(file)
      response.writeHead(200, { 'Content-Type': mime.get(extname(file)) ?? 'application/octet-stream' })
      response.end(body)
    } catch {
      response.writeHead(404).end()
    }
  })
  await new Promise((listen, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', listen)
  })
  let browser
  try {
    browser = await chromium.launch({ timeout: 10_000 })
    const page = await browser.newPage({ ...devices['Pixel 7'], serviceWorkers: 'block' })
    const origin = 'http://127.0.0.1:' + server.address().port
    const errors = []
    const addError = message => { if (errors.length < 10) errors.push(message.slice(0, 450)) }
    page.on('pageerror', error => addError(error.message))
    page.on('crash', () => addError('Browser page crashed.'))
    page.on('console', entry => { if (entry.type() === 'error') addError(entry.text()) })
    page.on('response', response => {
      if (response.status() >= 400) addError('HTTP ' + response.status() + ' ' + new URL(response.url()).pathname)
    })
    await page.route('**/*', route => {
      if (new URL(route.request().url()).origin === origin) return route.continue()
      addError('Game requested a resource outside its package.')
      return route.abort()
    })
    try {
      await page.goto(origin, { waitUntil: 'load', timeout: 8000 })
      const canvas = page.locator('#game canvas').first()
      await canvas.waitFor({ state: 'visible', timeout: 8000 })
      await canvas.tap({ timeout: 2500 })
      await page.waitForTimeout(400)
    } catch (error) {
      addError(error.message)
    }
    if (errors.length) throw new RejectedGame(21, errors.join('\n'))
  } finally {
    try {
      await browser?.close()
    } finally {
      server.close()
    }
  }
}

async function pack() {
  const files = []
  let size = 0
  async function walk(directory, prefix = '') {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const target = join(directory, entry.name)
      const path = prefix + entry.name
      if (entry.isDirectory()) {
        await walk(target, path + '/')
      } else if (entry.isFile()) {
        const data = await readFile(target)
        size += data.length
        if (size > maxArtifactSize || files.length === 512) {
          throw new RejectedGame(20, 'The compiled game exceeds the package limits.')
        }
        files.push({ path, data: data.toString('base64') })
      } else {
        throw new Error('Unexpected entry in dist.')
      }
    }
  }
  await walk(dist)
  return files
}

try {
  const files = JSON.parse(await readFile('/input.json', 'utf8'))
  await build(files)
  await testGame()
  process.stdout.write(JSON.stringify(await pack()))
} catch (error) {
  console.error((error.message || 'Game runner failed.').slice(0, 6000))
  process.exitCode = error instanceof RejectedGame ? error.code : 1
}
