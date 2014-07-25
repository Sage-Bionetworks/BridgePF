#!/usr/bin/env bash

export DEBIAN_FRONTEND=noninteractive

# All the commands will run as the root by vagrant provisioning
# unless the user is explicitly set.
# Add '/usr/local/bin' to root's $PATH variable.
export PATH=$PATH:/usr/local/bin

# Update
apt-get -q -y autoclean
apt-get -q -y autoremove
apt-get -q -y update
apt-get -q -y upgrade

# Tools
apt-get -q -y install zip
apt-get -q -y install git

# JavaScript
apt-get -q -y install nodejs
ln -s /usr/bin/nodejs /usr/bin/node
apt-get -q -y install phantomjs
apt-get -q -y install npm
npm install -g bower
npm install -g grunt-cli

# Sass
apt-get -q -y install ruby
gem install sass

# Build JavaScript
pushd .
cd /vagrant/public/app
npm install
bower --allow-root install
cd ../neurod
npm install
bower --allow-root install
cd ../shared
npm install
bower --allow-root install
cd ../consent
npm install
bower --allow-root install
cd ../
npm install
grunt release
popd

# Java
apt-get -q -y install openjdk-7-jdk

# Play
wget http://downloads.typesafe.com/play/2.2.4/play-2.2.4.zip
unzip play-2.2.4.zip
rm play-2.2.4.zip

# Redis
apt-get -q -y install redis-server

