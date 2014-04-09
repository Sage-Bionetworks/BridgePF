package controllers;

import org.springframework.beans.factory.annotation.Autowired;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import spring.MyTestSpringBean;

@org.springframework.stereotype.Controller
public class ApplicationInterface extends Controller {
	
	@Autowired
	public MyTestSpringBean bean;
	
    public Result test() {
    	return ok(Json.toJson(bean));
    }

}
