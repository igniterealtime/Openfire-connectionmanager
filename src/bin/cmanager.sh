#!/bin/sh

#
# $RCSfile$
# $Revision: 1194 $
# $Date: 2005-03-30 13:39:54 -0300 (Wed, 30 Mar 2005) $
#

# tries to determine arguments to launch Connection Manager

# Set JVM extra Setting
# JVM_SETTINGS="-Xms512m -Xmx1024m"
JVM_SETTINGS=""

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true
           if [ -z "$JAVA_HOME" ] ; then
             JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Home
           fi
           ;;
esac

#if cmanager home is not set or is not a directory
if [ -z "$CMANAGER_HOME" -o ! -d "$CMANAGER_HOME" ]; then

	if [ -d /opt/connection_manager ] ; then
		CMANAGER_HOME="/opt/connection_manager"
	fi

	if [ -d /usr/local/connection_manager ] ; then
		CMANAGER_HOME="/usr/local/connection_manager"
	fi

	if [ -d ${HOME}/opt/connection_manager ] ; then
		CMANAGER_HOME="${HOME}/opt/connection_manager"
	fi

	#resolve links - $0 may be a link in connection_manager's home
	PRG="0"
	progname=`basename "$0$"`

	# need this for relative symlinks

	# need this for relative symlinks
  	while [ -h "$PRG" ] ; do
    		ls=`ls -ld "$PRG"`
    		link=`expr "$ls" : '.*-> \(.*\)$'`
    		if expr "$link" : '/.*' > /dev/null; then
    			PRG="$link"
    		else
    			PRG=`dirname "$PRG"`"/$link"
    		fi
  	done

	#assumes we are in the bin directory
	CMANAGER_HOME=`dirname "$PRG"`/..

	#make it fully qualified
	CMANAGER_HOME=`cd "$CMANAGER_HOME" && pwd`
fi
CMANAGER_OPTS="${CMANAGER_OPTS} -DmanagerHome=${CMANAGER_HOME}"


# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
	[ -n "$CMANAGER_HOME" ] &&
    		CMANAGER_HOME=`cygpath --unix "$CMANAGER_HOME"`
  	[ -n "$JAVA_HOME" ] &&
    		JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
fi

#set the CMANAGER_LIB location
CMANAGER_LIB="${CMANAGER_HOME}/lib"
CMANAGER_OPTS="${CMANAGER_OPTS} -Dcmanager.lib.dir=${CMANAGER_LIB}"


if [ -z "$JAVACMD" ] ; then
  	if [ -n "$JAVA_HOME"  ] ; then
    		if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      			# IBM's JDK on AIX uses strange locations for the executables
      			JAVACMD="$JAVA_HOME/jre/sh/java"
    		else
      			JAVACMD="$JAVA_HOME/bin/java"
    		fi
  	else
    		JAVACMD=`which java 2> /dev/null `
    		if [ -z "$JAVACMD" ] ; then
        		JAVACMD=java
    		fi
  	fi
fi

if [ ! -x "$JAVACMD" ] ; then
  	echo "Error: JAVA_HOME is not defined correctly."
  	echo "  We cannot execute $JAVACMD"
  	exit 1
fi

if [ -z "$LOCALCLASSPATH" ] ; then
	LOCALCLASSPATH=$CMANAGER_LIB/startup.jar
else
      	LOCALCLASSPATH=$CMANAGER_LIB/startup.jar:$LOCALCLASSPATH
fi

# For Cygwin, switch paths to appropriate format before running java
if $cygwin; then
  	if [ "$OS" = "Windows_NT" ] && cygpath -m .>/dev/null 2>/dev/null ; then
    		format=mixed
  	else
    		format=windows
  	fi
  	CMANAGER_HOME=`cygpath --$format "$CMANAGER_HOME"`
  	CMANAGER_LIB=`cygpath --$format "$CMANAGER_LIB"`
  	JAVA_HOME=`cygpath --$format "$JAVA_HOME"`
  	LOCALCLASSPATH=`cygpath --path --$format "$LOCALCLASSPATH"`
  	if [ -n "$CLASSPATH" ] ; then
    		CLASSPATH=`cygpath --path --$format "$CLASSPATH"`
  	fi
  	CYGHOME=`cygpath --$format "$HOME"`
fi

# add a second backslash to variables terminated by a backslash under cygwin
if $cygwin; then
  case "$CMANAGER_HOME" in
    *\\ )
    CMANAGER_HOME="$CMANAGER_HOME\\"
    ;;
  esac
  case "$CYGHOME" in
    *\\ )
    CYGHOME="$CYGHOME\\"
    ;;
  esac
  case "$LOCALCLASSPATH" in
    *\\ )
    LOCALCLASSPATH="$LOCALCLASSPATH\\"
    ;;
  esac
  case "$CLASSPATH" in
    *\\ )
    CLASSPATH="$CLASSPATH\\"
    ;;
  esac
fi

cmanager_exec_command="exec \"$JAVACMD\" -server $JVM_SETTINGS $CMANAGER_OPTS -classpath \"$LOCALCLASSPATH\" -jar \"$CMANAGER_LIB\"/startup.jar"
eval $cmanager_exec_command
