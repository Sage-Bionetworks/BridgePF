# -*- mode: ruby -*-
# vi: set ft=ruby :

# Vagrantfile API/syntax version. Don't touch unless you know what you're doing!
VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|

  config.vm.box = "bento/ubuntu-15.04"
  config.vm.provision :shell, path: "bootstrap.sh"
  config.vm.network :forwarded_port, host: 6379, guest: 6379
  config.vm.network :forwarded_port, host: 9000, guest: 9000
  config.vm.network :forwarded_port, host: 9999, guest: 9999
  config.vm.network "private_network", ip: "192.168.55.123"
  config.vm.synced_folder "~/.sbt", "/home/vagrant/.sbt", nfs: true
  config.vm.synced_folder "~/.bridge", "/home/vagrant/.bridge", nfs: true

  config.vm.provider "virtualbox" do |v|
    v.customize ["modifyvm", :id, "--cpus", 2]
    v.customize ["modifyvm", :id, "--memory", 3000]
  end

end
