FROM openjdk:latest

RUN mkdir -p /srv/app && mkdir /srv/data

WORKDIR /srv/app

COPY ./target/project.jar /srv/app/

VOLUME /srv/data

ENTRYPOINT java -jar ./project.jar /srv/data
