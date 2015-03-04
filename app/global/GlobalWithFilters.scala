package global

import scala.concurrent.Future

import org.sagebionetworks.bridge.config.BridgeConfigFactory
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer
import org.springframework.context.support.ClassPathXmlApplicationContext

import filters.CorsFilter
import models.StatusMessage
import play.api.Application
import play.api.GlobalSettings
import play.api.Logger
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.HeaderNames.X_FORWARDED_PROTO
import play.api.http.Status.MOVED_PERMANENTLY
import play.api.mvc.Action
import play.api.mvc.Controller;
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.Redirect
import play.api.mvc.SimpleResult
import play.api.mvc.WithFilters
import play.filters.gzip.GzipFilter
import play.libs.Json

object GlobalWithFilters extends WithFilters (

    // TODO: CSRF filter

    CorsFilter,

    new GzipFilter(shouldGzip = (request, response) =>
        response.headers.get(CONTENT_TYPE).exists(_.contains(JSON)))

  ) with GlobalSettings {

  override def beforeStart(app: Application) {
    Logger.info("Environment: " + BridgeConfigFactory.getConfig().getEnvironment().name().toLowerCase())
    DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb")
  }

  override def onStart(app: Application) {
    val context = AppContext
  }

  override def getControllerInstance[T](controllerClass: Class[T]): T = {
    if (AppContext == null) {
      throw new IllegalStateException("application-context.xml is not initialized")
    }
    return AppContext.getBean(controllerClass)
  }

  override def onBadRequest(request: RequestHeader, error: String): Future[SimpleResult] = {
    val page = views.html.defaultpages.badRequest(request, Json.toJson(new StatusMessage(error)).toString())
    val results = BadRequest(page)
    Future.successful(results)
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    // Heroku redirect HTTP to HTTPS
    request.headers.get(X_FORWARDED_PROTO) match {
      case Some("http") => Some(Action {
          val path = "https://" + request.host + request.uri
          Redirect(path, MOVED_PERMANENTLY)
        })
      case _ => super.onRouteRequest(request)
    }
  }
}

object AppContext {
  private val context = new ClassPathXmlApplicationContext("application-context.xml")
  def getBean[T](controllerClass: Class[T]) = {
    context.getBean(controllerClass.getSimpleName()+"Proxied").asInstanceOf[T]
  }
}
