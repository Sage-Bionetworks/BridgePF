package controllers;

import java.security.SecureRandom;

import play.mvc.Result;

public class LunchController extends BaseController {

    private static final SecureRandom random = new SecureRandom();

    public Result where() throws Exception {
        String[] places = new String[] {
                "http://www.yelp.com/biz/yellow-dot-cafe-seattle",
                "http://www.yelp.com/biz/veggie-grill-seattle",
                "http://www.yelp.com/biz/portage-bay-café-seattle-8",
                "http://www.yelp.com/biz/brave-horse-tavern-seattle",
                "http://www.yelp.com/biz/cuoco-seattle",
                "http://www.yelp.com/biz/lunchbox-laboratory-seattle-2",
                "http://www.yelp.com/biz/jade-garden-seattle",
                "http://www.yelp.com/biz/saffron-grill-seattle"
        };
        return okResult(places[random.nextInt(places.length)]);
    }
}
