FROM node:22-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install --global npm@11.6.1 --no-audit --no-fund \
    && npm ci --ignore-scripts --no-audit --no-fund
COPY . .
RUN npm run build

FROM node:22-alpine

ENV NODE_ENV=production
WORKDIR /app
RUN addgroup --system --gid 10001 appgroup \
    && adduser --system --uid 10001 --ingroup appgroup appuser
COPY --from=build --chown=appuser:appgroup /app /app
USER appuser
EXPOSE 3000
CMD ["npm", "run", "start", "--", "--host", "0.0.0.0"]
