import Phaser from 'phaser'

export const WIDTH = 880
export const HEIGHT = 480

export function createTextures(scene: Phaser.Scene): void {
  if (scene.textures.exists('sunset')) return

  paintTexture(scene, 'sunset', WIDTH, HEIGHT, (ctx) => {
    const sky = ctx.createLinearGradient(0, 0, 0, HEIGHT)
    sky.addColorStop(0, '#483957')
    sky.addColorStop(0.52, '#a36578')
    sky.addColorStop(1, '#ebb485')
    ctx.fillStyle = sky
    ctx.fillRect(0, 0, WIDTH, HEIGHT)

    const light = ctx.createRadialGradient(580, 124, 22, 580, 124, 136)
    light.addColorStop(0, '#ffd293a0')
    light.addColorStop(1, '#ffd29300')
    ctx.fillStyle = light
    ctx.fillRect(440, 0, 280, 260)
    ctx.fillStyle = '#ffdda2'
    ctx.beginPath()
    ctx.arc(580, 124, 48, 0, Math.PI * 2)
    ctx.fill()

    ctx.fillStyle = '#ffd8ae66'
    for (const [x, y, width] of [[52, 96, 104], [300, 125, 141], [667, 83, 112], [414, 58, 58]]) {
      ctx.fillRect(x, y, width, 2)
      ctx.fillRect(x + 22, y + 8, width / 2, 2)
    }

    ridge(ctx, '#875a79', [[0, 298], [104, 215], [163, 244], [253, 164], [390, 284],
      [472, 242], [519, 283], [709, 169], [880, 267]])
    ridge(ctx, '#694969', [[0, 342], [143, 259], [233, 319], [341, 240], [487, 347],
      [554, 302], [638, 325], [769, 249], [880, 313]])
    ridge(ctx, '#4b3b56', [[0, 386], [93, 337], [228, 375], [381, 320], [556, 384],
      [695, 344], [816, 381], [880, 345]])

    ctx.fillStyle = '#3e334b'
    for (const [x, height] of [[29, 66], [70, 47], [124, 51], [297, 42], [352, 70],
      [450, 49], [524, 51], [573, 63], [724, 46], [832, 64], [865, 49]]) {
      const y = 420 - height
      ctx.fillRect(x - 2, y + height * 0.6, 4, height * 0.4)
      ctx.beginPath()
      ctx.moveTo(x, y)
      ctx.lineTo(x + height * 0.33, y + height * 0.79)
      ctx.lineTo(x - height * 0.33, y + height * 0.79)
      ctx.closePath()
      ctx.fill()
    }
  })

  paintTexture(scene, 'ground', 32, 18, (ctx) => {
    ctx.fillStyle = '#413049'
    ctx.fillRect(0, 0, 32, 18)
    ctx.fillStyle = '#79a774'
    ctx.fillRect(0, 0, 32, 5)
    ctx.fillStyle = '#b1c68a'
    ctx.fillRect(0, 0, 32, 2)
    ctx.fillStyle = '#4d695e'
    ctx.fillRect(0, 5, 32, 2)
  })

  paintTexture(scene, 'coin', 24, 28, (ctx) => {
    ctx.shadowColor = '#ffd787'
    ctx.shadowBlur = 8
    ctx.fillStyle = '#ffda76'
    ctx.beginPath()
    ctx.ellipse(12, 14, 9, 11, 0, 0, Math.PI * 2)
    ctx.fill()
    ctx.shadowBlur = 0
    ctx.fillStyle = '#c7793b'
    ctx.beginPath()
    ctx.ellipse(12, 14, 5.8, 8, 0, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = '#fff2a2'
    ctx.fillRect(11, 9, 2, 10)
  })

  paintTexture(scene, 'cat', 52, 46, (ctx) => {
    ctx.lineCap = 'round'
    ctx.strokeStyle = '#4b2b3c'
    ctx.lineWidth = 8
    ctx.beginPath()
    ctx.moveTo(16, 32)
    ctx.quadraticCurveTo(3, 35, 6, 22)
    ctx.stroke()
    ctx.strokeStyle = '#e87944'
    ctx.lineWidth = 5
    ctx.stroke()

    ctx.fillStyle = '#4b2b3c'
    ctx.beginPath()
    ctx.moveTo(14, 41)
    ctx.quadraticCurveTo(9, 29, 18, 24)
    ctx.lineTo(18, 8)
    ctx.quadraticCurveTo(18, 4, 22, 7)
    ctx.lineTo(29, 14)
    ctx.lineTo(36, 14)
    ctx.lineTo(43, 7)
    ctx.quadraticCurveTo(47, 4, 47, 9)
    ctx.lineTo(47, 25)
    ctx.quadraticCurveTo(53, 38, 44, 41)
    ctx.closePath()
    ctx.fill()

    ctx.fillStyle = '#f58b48'
    ctx.beginPath()
    ctx.moveTo(16, 38)
    ctx.quadraticCurveTo(12, 28, 21, 26)
    ctx.lineTo(21, 11)
    ctx.lineTo(28, 17)
    ctx.lineTo(37, 17)
    ctx.lineTo(44, 11)
    ctx.lineTo(44, 26)
    ctx.quadraticCurveTo(50, 36, 43, 38)
    ctx.closePath()
    ctx.fill()

    ctx.fillStyle = '#ffe4b6'
    ctx.fillRect(30, 32, 15, 6)
    ctx.fillRect(17, 38, 8, 3)
    ctx.fillRect(35, 38, 8, 3)
    ctx.fillStyle = '#4b2b3c'
    ctx.fillRect(27, 25, 3, 4)
    ctx.fillRect(39, 25, 3, 4)
    ctx.fillRect(33, 32, 4, 2)
  })
}

function paintTexture(
  scene: Phaser.Scene,
  key: string,
  width: number,
  height: number,
  paint: (context: CanvasRenderingContext2D) => void,
): void {
  const texture = scene.textures.createCanvas(key, width, height)
  if (!texture) throw new Error(`Could not create texture ${key}`)
  paint(texture.context)
  texture.refresh()
}

function ridge(ctx: CanvasRenderingContext2D, color: string, points: [number, number][]): void {
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.moveTo(0, HEIGHT)
  for (const [x, y] of points) ctx.lineTo(x, y)
  ctx.lineTo(WIDTH, HEIGHT)
  ctx.closePath()
  ctx.fill()
}
