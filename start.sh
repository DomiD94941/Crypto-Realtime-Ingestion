#!/bin/sh
set -e

case "$1" in
  producer)
    exec java -jar /app/producer.jar
    ;;
  consumer)
    exec java -jar /app/consumer.jar
    ;;
  *)
    exit 1
    ;;
esac
