#!/usr/bin/env sh

clj -T:build clean
clj -T:build jar
rm -rf ziptmp
unzip target/itt.jar -d ziptmp
tail -n+3 ziptmp/META-INF/maven/net.roghetti/itt/pom.properties > ziptmp/META-INF/maven/net.roghetti/itt/pom.properties.new
mv ziptmp/META-INF/maven/net.roghetti/itt/pom.properties.new ziptmp/META-INF/maven/net.roghetti/itt/pom.properties
find ziptmp -exec touch -t 198001010000 {} +
cd ziptmp
zip -r itt.jar *
cd ..
mv ziptmp/itt.jar target/itt.jar
cp ziptmp/META-INF/maven/net.roghetti/itt/pom.xml pom.xml
touch -t 198001010000 pom.xml
rm -rf ziptmp
strip-nondeterminism -t jar target/itt.jar
cp target/itt.jar itt.jar
