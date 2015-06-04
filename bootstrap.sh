#!/usr/bin/env bash

export DEBIAN_FRONTEND=noninteractive

# All the commands will run as the root by vagrant provisioning
# unless the user is explicitly set.
# Add '/usr/local/bin' to root's $PATH variable.
export PATH=$PATH:/usr/local/bin

# Update
apt-get -q -y update
apt-get -q -y upgrade

# Tools
apt-get -q -y install curl zip

# NFS Client
apt-get -q -y install nfs-common

# Java
apt-get -q -y install openjdk-8-jdk

export ACTIVATOR_VERSION=1.3.4

# Play
su - vagrant -c "wget http://downloads.typesafe.com/typesafe-activator/${ACTIVATOR_VERSION}/typesafe-activator-${ACTIVATOR_VERSION}.zip"
su - vagrant -c "rm -rf activator-${ACTIVATOR_VERSION}"
su - vagrant -c "unzip typesafe-activator-${ACTIVATOR_VERSION}.zip"
rm typesafe-activator-${ACTIVATOR_VERSION}.zip

# Redis
apt-get -q -y install redis-server

# .bash_profile
su - vagrant -c "echo 'source ~/.profile' > .bash_profile"
su - vagrant -c "echo 'export PATH=$PATH:~/activator-${ACTIVATOR_VERSION}' >> ~/.bash_profile"
su - vagrant -c "echo 'export SBT_OPTS=\"-Xmx2000M -Xss2M -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled\"' >> ~/.bash_profile"
su - vagrant -c "source ~/.bash_profile"
