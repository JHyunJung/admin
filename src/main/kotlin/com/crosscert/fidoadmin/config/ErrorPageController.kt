package com.crosscert.fidoadmin.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 컨테이너 오류 페이지([WebMvcConfig.errorPages])가 넘어오는 화면. 오류 디스패치는 원래 요청의 메서드를
 * 그대로 쓰므로(POST 가 403 이면 POST /error/403) 메서드를 가리지 않는다. 뷰 컨트롤러는 GET·HEAD 만 받아
 * POST 오류가 405 로 다시 깨졌다. 상태 코드는 컨테이너가 이미 정해 두었으므로 여기서 바꾸지 않는다.
 */
@Controller
internal class ErrorPageController {

    @RequestMapping("/error/{code:403|404|500}")
    fun page(@PathVariable code: String): String = "error/$code"
}
