# Stage 1: building the angular app
FROM node:20-alpine AS build
WORKDIR /app 
COPY package.json package-lock.json ./
RUN npm ci 
COPY . . 
RUN npm run build

# Stage 2: serve the built files with nginx
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/leaping-portfolio /usr/share/nginx/html 
EXPOSE 80


# 1st image has Node and all the build tools
# 2nd image only recieves the finished files (leaves node_modules behind)