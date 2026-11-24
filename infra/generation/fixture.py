"""Deterministic model and runner test doubles. The runner accepts only the two built-in sources."""

import base64
import http.server
import json
import os
from pathlib import Path
import subprocess
import sys

CREATE = "Create a cyan coin game with touch controls."
EDIT = "Change the coin color to orange."
DENY = "Fixture policy rejection."
SOURCE = """import Phaser from 'phaser'
class CoinGame extends Phaser.Scene {
  create(): void {
    const coin = this.add.circle(400, 300, 40, COLOR).setInteractive()
    const title = this.add.text(220, 120, 'Tap the coin', { fontSize: '40px' })
    coin.on('pointerdown', () => {
      coin.setVisible(false)
      title.setText('Coin collected! Tap here to replay').setInteractive()
      title.once('pointerdown', () => this.scene.restart())
    })
  }
}
new Phaser.Game({
  type: Phaser.CANVAS, parent: 'game', width: 800, height: 600, banner: false,
  scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
  audio: { noAudio: true }, scene: [CoinGame],
})
"""


def source(color):
    return [{"path": "src/main.ts", "content": SOURCE.replace("COLOR", color)}]


SOURCES = {"cyan": source("0x49dbe8"), "orange": source("0xffa44a")}


def prepare(directory):
    directory.mkdir(parents=True, exist_ok=True)
    for name, files in SOURCES.items():
        result = subprocess.run(
            ["docker", "run", "--rm", "--pull=never", "-i", "--network=none", "--read-only",
             "--cap-drop=ALL", "--security-opt=no-new-privileges:true", "--cpus=1",
             "--memory=1g", "--memory-swap=1g", "--pids-limit=128", "--shm-size=128m",
             "--tmpfs", "/work:rw,nosuid,nodev,size=256m,mode=1777",
             "--tmpfs", "/tmp:rw,nosuid,nodev,size=128m,mode=1777", "playly-game-runner"],
            input=json.dumps(files).encode(), stdout=subprocess.PIPE, check=True, timeout=150,
        )
        parsed = json.loads(result.stdout)
        assert any(entry["path"] == "index.html" for entry in parsed)
        assert any(base64.b64decode(entry["data"]) for entry in parsed if entry["path"].endswith(".js"))
        (directory / f"{name}.json").write_bytes(result.stdout)


def serve(directory):
    token = os.environ["PLAYLY_RUNNER_TOKEN"]
    artifacts = {color: (directory / f"{color}.json").read_bytes() for color in SOURCES}

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_POST(self):
            length = int(self.headers.get("Content-Length", 0))
            if not 0 < length <= 2 * 1024 * 1024:
                self.send_error(400)
                return
            try:
                payload = json.loads(self.rfile.read(length))
                if self.path == "/build":
                    if self.headers.get("Authorization") != "Bearer " + token:
                        self.send_error(404)
                        return
                    for color, files in SOURCES.items():
                        if payload == files:
                            self.send_json(artifacts[color])
                            return
                    self.send_error(400)
                    return
                context = json.loads(payload["messages"][-1]["content"])
                if self.path == "/generate":
                    prompt = context["brief"]
                    if prompt == CREATE and context["currentFiles"] == []:
                        files = SOURCES["cyan"]
                    elif prompt == EDIT and context["currentFiles"] == SOURCES["cyan"]:
                        files = SOURCES["orange"]
                    else:
                        self.send_error(400)
                        return
                    response = {"files": files, "done": True, "remaining": ""}
                elif self.path == "/judge":
                    allowed = context.get("request") != DENY
                    response = {"allowed": allowed, "score": int(allowed)}
                else:
                    self.send_error(404)
                    return
                self.send_json(json.dumps({"choices": [{"finish_reason": "stop",
                                                        "message": {"content": json.dumps(response)}}]}).encode())
            except (ValueError, KeyError, TypeError):
                self.send_error(400)

        def send_json(self, payload):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

    address = os.environ.get("PLAYLY_FIXTURE_BIND", "127.0.0.1")
    port = int(os.environ.get("PLAYLY_FIXTURE_PORT", "18091"))
    http.server.ThreadingHTTPServer((address, port), Handler).serve_forever()


if __name__ == "__main__":
    command, path = sys.argv[1:3]
    {"prepare": prepare, "serve": serve}[command](Path(path))
