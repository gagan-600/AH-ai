# frontend/Dockerfile -- updated and more robust

FROM node:20-alpine AS builder

# Optional build arg (your toolchain may replace this path with an accessible URL)
ARG PROJECT_ZIP_URL=/mnt/data/Handwritting-reading-AI-main.zip
ENV PROJECT_ZIP_URL=${PROJECT_ZIP_URL}

WORKDIR /app

# Copy package files (works with or without package-lock.json)
COPY package*.json ./

# Install dependencies: prefer `npm ci` if lockfile exists, otherwise fall back to `npm install`.
# Use --legacy-peer-deps to avoid strict peer dependency failures during container build.
RUN if [ -f package-lock.json ]; then \
      npm ci --legacy-peer-deps; \
    else \
      npm install --legacy-peer-deps; \
    fi

# Copy source and build
COPY . .
ENV NODE_ENV=production
RUN npm run build

# ---- runtime image ----
FROM node:20-alpine
WORKDIR /app

# Install serve to serve the static build (small and simple)
RUN npm install -g serve

# Copy built assets from builder stage
COPY --from=builder /app/build ./build

EXPOSE 3000

# Runtime env (frontend can read REACT_APP_BACKEND_URL from environment at container start)
ENV REACT_APP_BACKEND_URL="http://localhost:5000"
CMD ["serve", "-s", "build", "-l", "3000"]
