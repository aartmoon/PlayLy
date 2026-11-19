import Phaser from 'phaser'
import { HEIGHT, WIDTH } from './art'
import { bindControls } from './controls'
import { CoinScene } from './CoinScene'

const score = document.querySelector<HTMLOutputElement>('#coins')!
const win = document.querySelector<HTMLElement>('#win')!
const scene = new CoinScene(bindControls(), (collected, total) => {
  score.value = `${collected} / ${total}`
  win.hidden = collected < total
})

new Phaser.Game({
  type: Phaser.CANVAS,
  width: WIDTH,
  height: HEIGHT,
  parent: 'game',
  banner: false,
  audio: { noAudio: true },
  scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
  physics: { default: 'arcade', arcade: { gravity: { x: 0, y: 1140 } } },
  scene: [scene],
})

const restart = document.querySelector<HTMLButtonElement>('#restart')!
restart.addEventListener('click', (event) => {
  if (event.detail > 0) restart.blur()
  scene.scene.restart()
})
