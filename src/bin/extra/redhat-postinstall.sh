#!/bin/sh

# redhat-poinstall.sh
#
# This script sets permissions on the Connection Manager installtion
# and install the init script.
#
# Run this script as root after installation of Connection Manager
# It is expected that you are executing this script from the bin directory

# If you used an non standard directory name of location
# Please specify it here
# CMANAGER_HOME=
 
CMANAGER_USER="jive"
CMANAGER_GROUP="jive"

if [ ! $CMANAGER_HOME ]; then
	if [ -d "/opt/connection_manager" ]; then
		CMANAGER_HOME="/opt/connection_manager"
	elif [ -d "/usr/local/connection_manager" ]; then
		CMANAGER_HOME="/usr/local/connection_manager"
	fi
fi

# Grant execution permissions
chmod +x $CMANAGER_HOME/bin/extra/cmanagerd

# Install the init script
cp $CMANAGER_HOME/bin/extra/cmanagerd /etc/init.d
/sbin/chkconfig --add cmanagerd
/sbin/chkconfig cmanagerd on

# Create the jive user and group
/usr/sbin/groupadd $CMANAGER_GROUP
/usr/sbin/useradd $CMANAGER_USER -g $CMANAGER_GROUP -s /bin/bash

# Change the permissions on the installtion directory
/bin/chown -R $CMANAGER_USER:$CMANAGER_GROUP $CMANAGER_HOME
