FROM openjdk:18
ARG VERSION

COPY pkg /home/vassal
WORKDIR /home/vassal/VASSAL-$VERSION

EXPOSE 8388/tcp
CMD ["java", "-cp", "./lib/Vengine.jar", "VASSAL.chat.node.Server"]