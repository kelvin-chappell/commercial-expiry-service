package commercialexpiry.service

import play.api.{Logger => PlayLogger}

trait Logger {
  protected val logger: PlayLogger = PlayLogger(getClass)
}
