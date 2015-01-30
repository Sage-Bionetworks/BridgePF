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
apt-get -q -y install vim bzip2 curl zip git

# NFS Client
apt-get -q -y install nfs-common

# Java
apt-get -q -y install openjdk-7-jdk

# Play
su - vagrant -c "wget http://downloads.typesafe.com/play/2.2.6/play-2.2.6.zip"
su - vagrant -c "rm -rf play-2.2.6"
su - vagrant -c "unzip play-2.2.6.zip"
rm play-2.2.6.zip

# Redis
apt-get -q -y install redis-server

# .bash_profile
su - vagrant -c "echo 'source ~/.profile' > .bash_profile"
su - vagrant -c "echo 'export PATH=$PATH:~/play-2.2.6' >> ~/.bash_profile"
su - vagrant -c "echo 'export SBT_OPTS=\"-Xmx2000M -Xss2M -XX:PermSize=128M -XX:MaxPermSize=256M -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled\"' >> ~/.bash_profile"
su - vagrant -c "source ~/.bash_profile"
