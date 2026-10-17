JAVA BACKEND
============

All Java source files, compiled classes, the MySQL connector, and the
project instructions are stored in this directory. The classes intentionally
remain in the default Java package so the existing backend behavior and
database integration remain unchanged.

Compile from this directory:

  javac -encoding UTF-8 -cp ".;mysql-connector-j-26.7.0.jar" *.java

Run the JSON API:

  java -cp ".;mysql-connector-j-26.7.0.jar" ApiServer

Run the legacy HTML server:

  java -cp ".;mysql-connector-j-26.7.0.jar" WebServer
