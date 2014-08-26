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
apt-get -q -y install bzip2
apt-get -q -y install zip
apt-get -q -y install git

# NFS Client
apt-get -q -y install nfs-common

# node.js
apt-get -q -y install nodejs
ln -s /usr/bin/nodejs /usr/bin/node

# PhantomJS
apt-get -q -y install fontconfig freetype2-demos
su - vagrant -c "wget https://bitbucket.org/ariya/phantomjs/downloads/phantomjs-1.9.7-linux-x86_64.tar.bz2"
su - vagrant -c "bunzip2 phantomjs-1.9.7-linux-x86_64.tar.bz2"
su - vagrant -c "tar xvf phantomjs-1.9.7-linux-x86_64.tar"
su
rm phantomjs-1.9.7-linux-x86_64.tar
echo 'export PATH=$PATH:/home/vagrant/phantomjs-1.9.7-linux-x86_64/bin' >> /home/vagrant/.profile

# npm
apt-get -q -y install npm

# bower
npm install -g bower

# grunt
npm install -g grunt-cli

# Sass
apt-get -q -y install ruby
gem install sass

# Java
apt-get -q -y install openjdk-7-jdk

# Play
su - vagrant -c "wget http://downloads.typesafe.com/play/2.2.4/play-2.2.4.zip"
su - vagrant -c "unzip play-2.2.4.zip"
su
rm play-2.2.4.zip
echo 'export PATH=$PATH:/home/vagrant/play-2.2.4' >> /home/vagrant/.profile

# Redis
apt-get -q -y install redis-server

