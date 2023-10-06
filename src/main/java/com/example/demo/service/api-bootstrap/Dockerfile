# nginx state for serving content
FROM public.ecr.aws/nginx/nginx:alpine
##TEST USING THE FOLLOWING VERSION public.ecr.aws/nginx/nginx:1.20.0-alpine
# Set working directory to nginx asset directory
WORKDIR /usr/share/nginx/html
# Remove default nginx static assets
RUN rm -rf ./*
# Copy static assets over
COPY ./index.html ./
COPY ./nginx.conf /etc/nginx/
# set file permissions for nginx user
RUN chown -R nginx:nginx /var/cache/nginx /etc/nginx/
# User guest on Alpine
USER nginx
HEALTHCHECK CMD curl http://localhost:8080 || exit 1
# Containers run nginx with global directives and daemon off
ENTRYPOINT ["nginx", "-g", "daemon off;"]
