FROM openjdk:18
ARG VERSION

COPY tmp/VASSAL-$VERSION-linux.tar.bz2 /usr/src/vassal.tar.bz2
WORKDIR /usr/src
RUN tar xf vassal.tar.bz2
WORKDIR /usr/src/VASSAL-$Version

CMD ["java", "-cp", "./lib/Vengine.jar", "VASSAL.chat.node.Server"]