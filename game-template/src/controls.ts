export interface Controls {
  direction(): number
  consumeJump(): boolean
  reset(): void
}

export function bindControls(): Controls {
  const keys = new Set<string>()
  const left = new Set<number>()
  const right = new Set<number>()
  let jumpRequested = false

  window.addEventListener('keydown', (event) => {
    if (event.code === 'ArrowLeft' || event.code === 'ArrowRight') {
      event.preventDefault()
      keys.add(event.code)
    }
    if (event.code === 'Space' && !(event.target instanceof HTMLButtonElement)) {
      event.preventDefault()
      if (!event.repeat) jumpRequested = true
    }
  })
  window.addEventListener('keyup', (event) => keys.delete(event.code))

  bindMovement('left', left)
  bindMovement('right', right)

  const jumpButton = document.querySelector<HTMLButtonElement>('#jump')!
  jumpButton.addEventListener('pointerdown', (event) => {
    if (event.button === 0) jumpRequested = true
  })
  jumpButton.addEventListener('click', (event) => {
    if (event.detail === 0) jumpRequested = true
  })

  function reset(): void {
    keys.clear()
    left.clear()
    right.clear()
    jumpRequested = false
  }
  window.addEventListener('blur', reset)

  return {
    direction: () => Number(keys.has('ArrowRight') || right.size > 0)
      - Number(keys.has('ArrowLeft') || left.size > 0),
    consumeJump: () => {
      const requested = jumpRequested
      jumpRequested = false
      return requested
    },
    reset,
  }
}

function bindMovement(id: string, held: Set<number>): void {
  const button = document.querySelector<HTMLButtonElement>(`#${id}`)!
  button.addEventListener('pointerdown', (event) => {
    if (event.button !== 0) return
    event.preventDefault()
    button.setPointerCapture(event.pointerId)
    held.add(event.pointerId)
  })
  button.addEventListener('lostpointercapture', (event) => held.delete(event.pointerId))
}
