package com.aurora.player.core

import androidx.compose.ui.graphics.ImageBitmap

/**
 * สัญญาการเข้าถึงข้อมูลปกอัลบั้มที่เป็นกลางต่อแพลตฟอร์ม
 *
 * เหตุผลเชิงออกแบบ: การสกัด palette ต้องการ pixel access ระดับต่ำ
 * ซึ่ง Coil ไม่รับประกัน API ที่เสถียรข้ามแพลตฟอร์ม จึงแยกเส้นทางนี้
 * ออกจากเส้นทางการแสดงผล (AsyncImage) อย่างสิ้นเชิง
 *
 * @param maxDimension ขนาดเป้าหมายสูงสุดต่อด้าน — การ downsample ตั้งแต่
 *        ขั้นถอดรหัสช่วยลด peak memory ได้เชิงกำลังสอง O(k²)
 */
expect suspend fun loadArtworkBitmap(uri: String, maxDimension: Int = 128): ImageBitmap?
