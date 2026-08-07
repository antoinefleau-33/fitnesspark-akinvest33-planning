# syntax=docker/dockerfile:1

###############################################################################
# Étape 1 — Compilation de zsign (signature IPA sous Linux, sans Mac)
###############################################################################
FROM debian:bookworm-slim AS zsign-build

RUN apt-get update && apt-get install -y --no-install-recommends \
      git g++ make pkg-config libssl-dev ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
# zsign : https://github.com/zhlynn/zsign
RUN git clone --depth 1 https://github.com/zhlynn/zsign.git .
# Compilation via le Makefile officiel (zlib + minizip sont vendorisés dans src/third-party).
RUN make -C build/linux clean && make -C build/linux -j"$(nproc)" \
    && cp bin/zsign /usr/local/bin/zsign \
    && strip /usr/local/bin/zsign \
    && /usr/local/bin/zsign -v

###############################################################################
# Étape 2 — Runtime Node
###############################################################################
FROM node:20-bookworm-slim AS runtime

# libssl3 = dépendance d'exécution de zsign (libcrypto), unzip utile pour debug.
RUN apt-get update && apt-get install -y --no-install-recommends \
      libssl3 zlib1g ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=zsign-build /usr/local/bin/zsign /usr/local/bin/zsign

WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev

COPY server.js ./
COPY public ./public

ENV NODE_ENV=production \
    PORT=3000 \
    ZSIGN_BIN=/usr/local/bin/zsign \
    WORK_ROOT=/tmp/ipa-signer

EXPOSE 3000
CMD ["node", "server.js"]
