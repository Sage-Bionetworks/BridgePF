function TimeSeries() {
    this.array = [];
    this.labels = [];
    this.fields = [];
    this.originalData = {};
    this.changed = false;
}
TimeSeries.prototype = {
    clear: function() {
        this.array = [];
        this.originalData = {};
        this.changed = true;
    },
    convert: function(array) {
        this.array = [];
        this.originalData = {};
        if (array && array.length) {
            this.computeLabels(array[0]);
            for (var i=0, len = array.length; i < len; i++) {
                this.add(array[i]);
            }
            this.recompute();
        }
        this.changed = true;
    },
    convertOne: function(entry) {
        if (entry) {
            this.computeLabels(entry);
            this.add(entry);
            this.recompute();
            this.changed = true;
        }
    },
    add: function(entry) {
        if (this.originalData[entry.startDate]) {
            this.originalData[entry.startDate].push(entry);
        } else {
            this.originalData[entry.startDate] = [entry];
        }
    },
    computeLabels: function(entry) {
        if (this.labels.length === 0) {
            this.labels.push('Date');
            for (var prop in entry.data) {
                this.labels.push(prop);
                this.fields.push(prop);
            }
        }
    },
    recompute: function() {
        function average(array, field) {
            return array.reduce(function(sum, entry) {
                return sum + entry.data[field];
            }, 0) / array.length;
        }
        
        this.array = [];
        for (var time in this.originalData) {
            var series = this.originalData[time];
            var entry = [time];
            this.fields.forEach(function(field) {
                entry.push(average(series, field));
            });
            this.array.push(entry);
        }
        this.array.sort(function(a,b) {
            return a[0] - b[0];
        });
    },
    remove: function(record) {
        for (var time in this.originalData) {
            var series = this.originalData[time];
            for (var i=0; i < series.length; i++) {
                var r = series[i];
                if (r.recordId === record.recordId) {
                    series.splice(i, 1);
                    // If the series contains one item and it is removed, 
                    // then the entire entry by date needs to be removed as well.
                    if (series.length === 0) {
                        delete this.originalData[time];
                    }
                    this.recompute();
                    this.changed = true;
                    return;
                }
            }
        }
    },
    update: function(record) {
        for (var time in this.originalData) {
            var series = this.originalData[time];
            for (var i=0; i < series.length; i++) {
                var r = series[i];
                if (r.recordId === record.recordId) {
                    series[i] = record;
                    // time is a string, and series[i].startDate is a number.
                    if (series[i].startDate != time) {
                        this.add(record);

                        // Need to delete if it is the only entry.
                        if (series.length === 1) {
                            delete this.originalData[time];
                        }
                    }
                    this.recompute();
                    this.changed = true;
                    return;
                }
            }
        }
    },
    hasChanged: function() {
        var c = this.changed;
        this.changed = false;
        return c;
    }
};
