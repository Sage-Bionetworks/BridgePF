package global

import filters._
import models.StatusMessage

import org.sagebionetworks.bridge.config.BridgeConfigFactory
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer

import org.springframework.context.ApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.filters.csrf.CSRFFilter
import play.filters.gzip.GzipFilter
import play.libs.Json
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

object GlobalWithFilters extends WithFilters(

  new GzipFilter(shouldGzip = (request, response) =>
    response.headers.get("Content-Type").exists(_.startsWith("text/html"))
  )

) with GlobalSettings {

  override def beforeStart(app: Application) {
    Logger.info("Environment: " + BridgeConfigFactory.getConfig().getEnvironment().getEnvName())
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
    request.headers.get("x-forwarded-proto") match {
      case Some("http") => Some(Action {
          val path = "https://" + request.host + request.path
          Redirect(path, 301)
      })
      case Some("https") => super.onRouteRequest(request)
      case None => super.onRouteRequest(request)
    }
  }
}

object AppContext {
  private val context = new ClassPathXmlApplicationContext("application-context.xml")
  def getBean[T](controllerClass: Class[T]) = {
    context.getBean(controllerClass)
  }
}
