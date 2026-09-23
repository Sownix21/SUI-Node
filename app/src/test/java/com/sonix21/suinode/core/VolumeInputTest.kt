package com.sonix21.suinode.core

import org.junit.Assert.*
import org.junit.Test

class VolumeInputTest {
    @Test fun preservesExistingBytesExactly() {
        for (bytes in listOf(0L, 1L, 123456789L, 1073741824L, Long.MAX_VALUE)) {
            assertEquals(bytes, VolumeInput.parse(VolumeInput.format(bytes)))
        }
    }
    @Test fun supportsFractionalAndPersianQuotas() {
        assertEquals(1610612736L, VolumeInput.parse("1.5"))
        assertEquals(1610612736L, VolumeInput.parse("۱٫۵"))
        assertEquals(0L, VolumeInput.parse(""))
    }
    @Test fun rejectsInvalidNegativeAndOverflowValues() {
        for (input in listOf("-1", "abc", "1.2.3", "9999999999999999999999", "1e100")) assertNull(VolumeInput.parse(input))
    }
}
