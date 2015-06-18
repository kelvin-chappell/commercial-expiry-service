package commercialexpiry.data

import com.gu.contentapi.client.ContentApiClientLogic
import commercialexpiry.Config
import dispatch.Http

class CapiClient extends ContentApiClientLogic {

  override protected val http: Http = {
    Http configure (_.setAllowPoolingConnections(true))
  }

  override val targetUrl: String = Config.capiUrl

  override val apiKey: String = Config.capiKey
}
