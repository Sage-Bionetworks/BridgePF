package global

import org.specs2.runner._
import org.junit.runner._
import play.api._
import play.api.libs.json._
import play.api.mvc._
import play.api.test._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
object GlobalWithFiltersSpec extends PlaySpecification {

  "gzip filter" should {
    "do gzip on JSON response" in {
      running(FakeApplication()) {
        header(CONTENT_ENCODING,
          GlobalWithFilters.doFilter(Action {
            Results.Ok(Json.obj("name" -> "bob", "age" -> 31))
          })(FakeRequest().withHeaders(ACCEPT_ENCODING -> "gzip")).run
        ) must beSome("gzip")
      }
    }
  }

  "HTTP" should {
    "redirect to HTTPS for Heroku" in new WithApplication{
      val result = route(FakeRequest(GET, "/").withHeaders(X_FORWARDED_PROTO -> "http")).get
      status(result) must equalTo(301)
    }
  }
}
