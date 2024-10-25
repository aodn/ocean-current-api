package au.org.aodn.oceancurrent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeService {
    public String test() {
        return "Ocean Current Test";
    }
}
