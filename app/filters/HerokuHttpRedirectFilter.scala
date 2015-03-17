package filters

import play.api.http.HeaderNames._
import play.api.http.Status._
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object HerokuHttpRedirectFilter extends Filter {
  def apply(nextFilter: (RequestHeader) => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {
    requestHeader.headers.get(X_FORWARDED_PROTO) match {
      case Some("http") => {
        val path = "https://" + requestHeader.host + requestHeader.uri
        Future(Redirect(path, MOVED_PERMANENTLY))
      }
      case _ => nextFilter(requestHeader)
    }
  }
}
