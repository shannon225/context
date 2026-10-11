package org.searlelab.context.io;

import java.io.File;

record AssayScheduleInputPair(int seed, File acquisitionFile, File assayScheduleFile) {
}
