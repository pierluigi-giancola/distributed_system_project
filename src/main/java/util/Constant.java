package util;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;

public class Constant {

    public static final int MEASUREMENT_BUFFER_CAPACITY = 24;

    public static final double MEASUREMENT_SLIDING_WINDOW = 0.5;

    public static final String DEFAULT_SERVER_IP = "localhost";

    public static final int DEFAULT_SERVER_PORT = 8080;

    public static final int BOOST_TOKEN = 2;

    public static final URI DEFAULT_SERVER_URI = UriBuilder.fromUri("http://"+ Constant.DEFAULT_SERVER_IP).port(Constant.DEFAULT_SERVER_PORT).build();

    public static final String CONDOM_ID = "";

    public static class Path {
        private static final String SEP = "/";
        //House
        public static final String HOUSES = "houses";
        public static final String ADD = "add";
        public static final String HOUSE_ADD = HOUSES +SEP+ ADD;
        public static final String DELETE = "delete";
        public static final String HOUSE_DELETE = HOUSES +SEP+ DELETE;
        public static final String UPDATE = "update";
        public static final String HOUSE_UPDATE = HOUSES +SEP+ UPDATE;

        //Admin
        public static final String ADMIN = "admin";
        public static final String ADMIN_CONDOM  = ADMIN +SEP+ HOUSES;
        private static final String LAST = "last";
        private static final String MEASUREMENTS = "measurements";
        private static final String STATISTICS = "statistics";
        public static final String HOUSE_ID_PARAM = "houseId";
        public static final String NUM_PARAM = "num";
        private static final String HOUSE_ID_PARAM_BRACKETS = "{"+HOUSE_ID_PARAM+":([^/]*/)?}";
        private static final String NUM_PARAM_BRACKETS = "{"+NUM_PARAM+": [1-9]+[0-9]*}";
        public static final String LAST_MEASUREMENTS = HOUSE_ID_PARAM_BRACKETS + LAST +SEP+ NUM_PARAM_BRACKETS +SEP+ MEASUREMENTS;
        public static final String LAST_STATISTICS = HOUSE_ID_PARAM_BRACKETS + LAST +SEP+ NUM_PARAM_BRACKETS +SEP+ STATISTICS;

        public static String getLastMeasurementsPath(String id, int lastN) {
            return ADMIN_CONDOM +SEP+ id +SEP+ LAST +SEP+ lastN +SEP+ MEASUREMENTS;
        }

        public static String getLastStatisticsPath(String id, int lastN) {
            return ADMIN_CONDOM +SEP+ id +SEP+ LAST +SEP+ lastN +SEP+ STATISTICS;
        }
    }

}
