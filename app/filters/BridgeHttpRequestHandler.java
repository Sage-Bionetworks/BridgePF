package filters;

import static play.mvc.Http.HeaderNames.X_FORWARDED_PROTO;
import static play.mvc.Http.Status.MOVED_PERMANENTLY;

import java.lang.reflect.Method;

import play.http.DefaultHttpRequestHandler;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Context;
import play.mvc.Result;

public class BridgeHttpRequestHandler extends DefaultHttpRequestHandler {

    @SuppressWarnings("rawtypes")
    @Override
    public Action createAction(final Http.Request request, Method actionMethod) {
        String[] xForwardedProto = request.headers().get(X_FORWARDED_PROTO);
        if (xForwardedProto != null && xForwardedProto.length > 0) {
            if ("HTTP".equalsIgnoreCase(xForwardedProto[0])) {
                return new Action.Simple() {
                    @Override
                    public Promise<Result> call(Context context) throws Throwable {
                        return Promise.<Result>pure(new Redirect(MOVED_PERMANENTLY, 
                                "https://" + request.host() + request.uri()));
                    }
                };
            }
        }
        return super.createAction(request, actionMethod);
    }
}
