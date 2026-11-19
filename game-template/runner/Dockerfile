FROM mcr.microsoft.com/playwright:v1.62.1-noble

WORKDIR /runner
COPY package.json package-lock.json tsconfig.json vite.config.ts ./
RUN npm ci --ignore-scripts --no-audit --no-fund \
    && npm install --prefix /browser --ignore-scripts --no-audit --no-fund playwright@1.62.1
COPY runner/index.html runner/shell.css runner/build.mjs ./

ENV HOME=/tmp NO_COLOR=1
USER pwuser
ENTRYPOINT ["node", "/runner/build.mjs"]
