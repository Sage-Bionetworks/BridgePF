function EmptyDataSet() {
    this.array = [];
    this.labels = [];
    this.fields = [];
    this.changed = false;
}
EmptyDataSet.prototype = {
    clear: function() {
    },
    convert: function(array) {
    },
    convertOne: function(entry) {
    },
    add: function(entry) {
    },
    computeLabels: function(entry) {
    },
    recompute: function() {
    },
    remove: function(record) {
    },
    update: function(record) {
    },
    hasChanged: function() {
        return this.changed;
    }
};
