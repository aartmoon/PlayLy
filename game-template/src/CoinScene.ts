import Phaser from 'phaser'
import { createTextures, HEIGHT, WIDTH } from './art'
import type { Controls } from './controls'

const PLATFORMS = [
  { x: 440, y: 434, width: WIDTH, height: 36 },
  { x: 205, y: 345, width: 154, height: 16 },
  { x: 415, y: 281, width: 166, height: 16 },
  { x: 636, y: 213, width: 162, height: 16 },
  { x: 761, y: 342, width: 148, height: 16 },
]
const COINS = [[108, 397], [201, 310], [415, 247], [637, 172], [779, 308]]
const SPEED = 265
const JUMP_SPEED = 550

export class CoinScene extends Phaser.Scene {
  private cat!: Phaser.Physics.Arcade.Sprite
  private coinsCollected = 0
  private jumpUntil = 0

  constructor(
    private readonly controls: Controls,
    private readonly reportScore: (collected: number, total: number) => void,
  ) {
    super('coins')
  }

  create(): void {
    this.controls.reset()
    this.coinsCollected = 0
    this.jumpUntil = 0
    this.reportScore(0, COINS.length)
    createTextures(this)
    this.add.image(WIDTH / 2, HEIGHT / 2, 'sunset')

    const platforms = this.physics.add.staticGroup()
    for (const { x, y, width, height } of PLATFORMS) {
      const platform = this.physics.add.staticImage(x, y, 'ground').setDisplaySize(width, height).refreshBody()
        .setDepth(1)
      // Platforms support the cat on landing and let it jump through from below.
      const collision = platform.body!.checkCollision
      collision.down = false
      collision.left = false
      collision.right = false
      platforms.add(platform)
    }

    this.cat = this.physics.add.sprite(52, 389, 'cat').setCollideWorldBounds(true).setDepth(3)
    this.cat.setBodySize(34, 31).setOffset(12, 10)
    this.physics.add.collider(this.cat, platforms)

    const coins = this.physics.add.staticGroup()
    for (const [x, y] of COINS) {
      coins.add(this.physics.add.staticImage(x, y, 'coin').setDepth(2))
    }
    this.physics.add.overlap(this.cat, coins, (_cat, collected) => {
      if (!(collected instanceof Phaser.Physics.Arcade.Image)) return
      collected.disableBody(true, true)
      this.coinsCollected++
      this.reportScore(this.coinsCollected, COINS.length)
    })
  }

  update(time: number): void {
    const direction = this.controls.direction()
    this.cat.setVelocityX(direction * SPEED)
    if (direction !== 0) this.cat.setFlipX(direction < 0)

    if (this.controls.consumeJump()) this.jumpUntil = time + 100
    if (this.jumpUntil > time && this.cat.body?.blocked.down) {
      this.cat.setVelocityY(-JUMP_SPEED)
      this.jumpUntil = 0
    }
  }
}
