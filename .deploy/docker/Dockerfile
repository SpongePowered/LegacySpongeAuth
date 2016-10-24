FROM java:latest
MAINTAINER Walker Crouse <windy@spongepowered.org>

COPY target/universal/spongeauth.tgz /app.tgz
RUN tar -xf /app.tgz && sh -c 'ln -s /spongeauth-* /app'

EXPOSE 9000

ARG BUILD_NUMBER
ARG GIT_BRANCH
ARG GIT_COMMIT
ARG JOB_NAME
ARG BUILD_TAG
ENV BUILD_NUMBER ${BUILD_NUMBER}
ENV GIT_BRANCH ${GIT_BRANCH}
ENV GIT_COMMIT ${GIT_COMMIT}
ENV JOB_NAME ${JOB_NAME}
ENV BUILD_TAG ${BUILD_TAG}

CMD ["/app/bin/spongeauth"]
