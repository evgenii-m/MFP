FROM ubuntu:20.04

ENV TZ=Europe/Moscow \
    DEBIAN_FRONTEND=noninteractive
RUN echo 'APT::Install-Suggests "0";' >> /etc/apt/apt.conf.d/00-docker
RUN echo 'APT::Install-Recommends "0";' >> /etc/apt/apt.conf.d/00-docker
RUN DEBIAN_FRONTEND=noninteractive \
  apt-get update \
  && apt-get install -y python3-pip ffmpeg make git zlib1g-dev libssl-dev \
  && apt-get install -y openjdk-11-jdk  \
  && apt-get install -y locales  \
  && rm -rf /var/lib/apt/lists/*

RUN python3 -m pip install -U yt-dlp[default]

RUN sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

EXPOSE 8000

COPY artifacts/music-feed-platform.jar app.jar

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", "/app.jar"]