package org.sagebionetworks.bridge.migration;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.HealthDataDao;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.springframework.context.support.AbstractApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

class HealthDataDumper {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        final AbstractApplicationContext appContext = Utils.loadAppContext();
        HealthDataDumper dataDumper = new HealthDataDumper(Environment.UAT, "heroku", appContext);
        DateTime start = new DateTime(2015, 7, 7, 0, 0);
        DateTime end = new DateTime(2015, 7, 10, 0, 0);
        String output = "health-data-staging-0707-0710";
        dataDumper.dump(start, end, output,
                new HealthDataStudyFilter(appContext, "cardiovascular"),
                new HealthDataAppVersionFilter("1.0.8"));
    }

    private final AbstractApplicationContext appContext;

    HealthDataDumper(final Environment env, final String user) {
        Utils.checkEnvironmentUser(env, user);
        appContext = Utils.loadAppContext();
    }

    HealthDataDumper(final Environment env, final String user, final AbstractApplicationContext appContext) {
        Utils.checkEnvironmentUser(env, user);
        checkNotNull(appContext);
        this.appContext = appContext;
    }

    /**
     * @param startDay  Starting day, inclusive
     * @param endDay   Ending day, exclusive
     * @param outputFile
     * @throws IOException 
     * @throws FileNotFoundException 
     */
    void dump(final DateTime startDay, final DateTime endDay, final String outputFile, final HealthDataFilter... filters)
            throws FileNotFoundException, IOException {

        checkNotNull(startDay);
        checkNotNull(endDay);
        checkNotNull(outputFile);

        final Path file = Paths.get(outputFile);
        if (Files.exists(file)) {
            throw new RuntimeException("File " + outputFile + " already exists.");
        }

        try (final FileOutputStream fos = new FileOutputStream(Files.createFile(file).toFile());
                final OutputStreamWriter osw = new OutputStreamWriter(fos);
                final Writer writer = new BufferedWriter(osw)) {

            final ObjectMapper mapper = new ObjectMapper();
            final HealthDataDao hdDao = appContext.getBean(HealthDataDao.class);

            DateTime start = startDay;
            while (start.isBefore(endDay)) {
                final String dayStr = start.toString("yyyy-MM-dd");
                System.out.println("Processing " + dayStr);
                final List<HealthDataRecord> records = hdDao.getRecordsForUploadDate(dayStr);
                for (final HealthDataRecord record : records) {
                    boolean keep = true;
                    for (final HealthDataFilter filter : filters) {
                        keep = keep && filter.keep(record);
                    }
                    if (keep) {
                        writer.write(mapper.writeValueAsString(record));
                        writer.write("\r\n");
                    }
                }
                start = start.plusDays(1);
            }
        }
    }
}
