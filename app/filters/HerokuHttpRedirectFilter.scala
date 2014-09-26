package filters

import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object HerokuHttpRedirectFilter extends Filter {
  def apply(nextFilter: (RequestHeader) => Future[SimpleResult])
           (requestHeader: RequestHeader): Future[SimpleResult] = {
    requestHeader.headers.get("x-forwarded-proto") match {
      case Some("http") => {
        val path = "https://" + requestHeader.host + requestHeader.path
        Future(Redirect(path, 301))
      }
      case Some("https") => nextFilter(requestHeader)
      case None => nextFilter(requestHeader)
    }
  }
}
