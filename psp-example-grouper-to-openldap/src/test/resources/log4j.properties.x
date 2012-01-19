# see http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html

log4j.appender.psp-stdout                           = org.apache.log4j.ConsoleAppender
log4j.appender.psp-stdout.Target                    = System.out
log4j.appender.psp-stdout.layout                    = org.apache.log4j.PatternLayout
log4j.appender.psp-stdout.layout.ConversionPattern  = %d{ISO8601}: [%t] %X{requestID} %-5p %C{1}.%M(%L) - %m%n

log4j.appender.psp-file                            = org.apache.log4j.DailyRollingFileAppender
log4j.appender.psp-file.File                       = logs/psp.log
log4j.appender.psp-file.DatePattern                = '.'yyyy-MM-dd
log4j.appender.psp-file.layout                     = org.apache.log4j.PatternLayout
log4j.appender.psp-file.layout.ConversionPattern   = %d{ISO8601}: [%t] %X{requestID} %-5p %C{1}.%M(%L) - %m%n

# root logger
log4j.rootLogger  = DEBUG, psp-stdout, psp-file

# psp
log4j.logger.edu.internet2.middleware.psp = INFO

# grouper
log4j.logger.edu.internet2.middleware.grouper = WARN

# grouper plugin to shibboleth
log4j.logger.edu.internet2.middleware.grouper.shibboleth = INFO

# vt-ldap
log4j.logger.edu.vt.middleware.ldap = DEBUG

log4j.logger.net.sf.ehcache = WARN
log4j.logger.org.hibernate = WARN
log4j.logger.org.springframework = WARN