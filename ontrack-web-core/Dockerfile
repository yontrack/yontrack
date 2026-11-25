FROM node:24-alpine AS base

# Install dependencies only when needed
FROM base AS deps
# Check https://github.com/nodejs/docker-node/tree/b4117f9333da4138b03a546ec926ef50a31506c3#nodealpine to understand why libc6-compat might be needed.
RUN apk add --no-cache libc6-compat
WORKDIR /app

# Install dependencies based on the preferred package manager
COPY package.json yarn.lock* package-lock.json* pnpm-lock.yaml* ./
RUN \
  if [ -f yarn.lock ]; then yarn --frozen-lockfile; \
  elif [ -f package-lock.json ]; then npm ci; \
  elif [ -f pnpm-lock.yaml ]; then yarn global add pnpm && pnpm i --frozen-lockfile; \
  else echo "Lockfile not found." && exit 1; \
  fi


# The third-party notices of the image (#2182): the same packages as the SBOM of the UI
# (`./gradlew :ontrack-web-core:sbom`), with the license, notice and copying files they package.
# See notices/third-party-notices.js. Only the installed dependencies are needed, not the build.
FROM deps AS notices
ARG VERSION=dev
COPY notices ./notices
RUN npm sbom --sbom-format cyclonedx --sbom-type application --omit dev --omit optional > /tmp/sbom.cdx.json \
 && node notices/third-party-notices.js /tmp/sbom.cdx.json node_modules notices/licenses \
      "Yontrack ${VERSION} - UI image" > THIRD-PARTY-NOTICES.txt


# Rebuild the source code only when needed
FROM base AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Next.js collects completely anonymous telemetry data about general usage.
# Learn more here: https://nextjs.org/telemetry
# Uncomment the following line in case you want to disable telemetry during the build.
# ENV NEXT_TELEMETRY_DISABLED 1

RUN yarn build

# If using npm comment out above and use below instead
# RUN npm run build

# Production image, copy all the files and run next
FROM base AS runner
WORKDIR /app

ENV NODE_ENV production
# Uncomment the following line in case you want to disable telemetry during runtime.
# ENV NEXT_TELEMETRY_DISABLED 1

# #1811 Take the Alpine security fixes published since the base image was built (libssl3 /
# libcrypto3 in particular), and remove the package managers the runtime does not need: the
# standalone Next.js server only runs `node server.js`, so npm, npx, corepack and yarn are dead
# weight whose bundled dependencies (tar, ip-address, brace-expansion, ...) are reported by the
# image vulnerability scan.
RUN apk upgrade --no-cache \
 && rm -rf /usr/local/lib/node_modules/npm \
           /usr/local/lib/node_modules/corepack \
           /usr/local/bin/npm \
           /usr/local/bin/npx \
           /usr/local/bin/corepack \
           /usr/local/bin/yarn \
           /usr/local/bin/yarnpkg \
           /opt/yarn-*

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public

# Set the correct permission for prerender cache
RUN mkdir .next
RUN chown nextjs:nodejs .next

# Automatically leverage output traces to reduce image size
# https://nextjs.org/docs/advanced-features/output-file-tracing
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=notices /app/THIRD-PARTY-NOTICES.txt ./
# The licence of Yontrack, from the root of the repository, passed as the `root` build context
# (`--build-context root=..`).
COPY --from=root LICENSE ./

# `sharp` and its LGPL-3.0 native build stay out of the image, and out of its SBOM - see the
# `outputFileTracingExcludes` of next.config.js.
RUN if [ -e node_modules/sharp ] || [ -e node_modules/@img ]; then \
      echo "The standalone output ships sharp - see next.config.js" >&2; exit 1; \
    fi

USER nextjs

EXPOSE 3000

ENV PORT 3000
# set hostname to localhost
ENV HOSTNAME "0.0.0.0"

# server.js is created by next build from the standalone output
# https://nextjs.org/docs/pages/api-reference/next-config-js/output
CMD ["node", "server.js"]
