package commercialexpiry.service

import ch.qos.logback.classic.{Logger => LogbackLogger}
import ch.qos.logback.core.util.Duration
import commercialexpiry.Config.{aws, logstash}
import net.logstash.logback.appender.LogstashTcpSocketAppender
import net.logstash.logback.encoder.LogstashEncoder
import play.api.LoggerLike

object LogstashBootstrapper extends AwsInstanceTags with Logger {

  def bootstrap() {
    logger.info("bootstrapping logstash appender if configured correctly")
    for (l <- asLogBack(logger)) {

      logger.info(s"bootstrapping logstash appender with ${aws.stack} -> ${aws.app} -> ${aws.stage}")
      val context = l.getLoggerContext

      val encoder = new LogstashEncoder()
      encoder.setContext(context)
      encoder.setCustomFields( s"""{"stack":"${aws.stack}","app":"${aws.app}","stage":"${aws.stage}"}""")
      encoder.start()

      val appender = new LogstashTcpSocketAppender()
      appender.setContext(context)
      appender.setEncoder(encoder)
      appender.addDestination(s"${logstash.host}:${logstash.port}")
      appender.setKeepAliveDuration(Duration.buildBySeconds(30.0))
      appender.start()

      l.addAppender(appender)
    }
  }

  def asLogBack(l: LoggerLike): Option[LogbackLogger] = l.logger match {
    case l: LogbackLogger => Some(l)
    case _ => None
  }
}
