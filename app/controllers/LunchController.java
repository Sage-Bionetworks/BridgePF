package controllers;

import java.security.SecureRandom;

import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller("lunchController")
public class LunchController extends BaseController {

    private static final SecureRandom random = new SecureRandom();

    public Result where() throws Exception {
        String[] places = new String[] {
                "http://www.yelp.com/biz/yellow-dot-cafe-seattle",
                "http://www.yelp.com/biz/veggie-grill-seattle",
                "http://www.yelp.com/biz/portage-bay-cafe-seattle-8",
                "http://www.yelp.com/biz/brave-horse-tavern-seattle",
                "http://www.yelp.com/biz/cuoco-seattle",
                "http://www.yelp.com/biz/lunchbox-laboratory-seattle-2",
                "http://www.yelp.com/biz/jade-garden-seattle",
                "http://www.yelp.com/biz/saffron-grill-seattle"
        };
        return temporaryRedirect(places[random.nextInt(places.length)]);
    }
}
