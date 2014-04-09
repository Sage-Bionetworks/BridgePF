package controllers;

import play.mvc.*;

@org.springframework.stereotype.Controller
public class Application extends Controller {

    public Result redirectToApp() {
    	return redirect("/index.html"); 
    }
    
}
