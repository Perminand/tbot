package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.service.InstrumentService;
import ru.perminov.dto.ShareDto;
import ru.perminov.dto.BondDto;
import ru.perminov.dto.EtfDto;
import ru.perminov.dto.InstrumentsCountDto;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Etf;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
@Slf4j
public class InstrumentController {
    private final InstrumentService instrumentService;

    @GetMapping("/shares")
    public List<ShareDto> getShares(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "status", defaultValue = "") String status) {
        try {
            return instrumentService.getAllShares().stream()
                    .filter(share -> search.isEmpty() || 
                            share.getTicker().toLowerCase().contains(search.toLowerCase()) ||
                            share.getName().toLowerCase().contains(search.toLowerCase()))
                    .filter(share -> status.isEmpty() || share.getTradingStatus().name().equalsIgnoreCase(status))
                    .skip(page * size)
                    .limit(size)
                    .map(ShareDto::from)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting shares", e);
            throw new RuntimeException("Error getting shares: " + e.getMessage(), e);
        }
    }

    @GetMapping("/bonds")
    public List<BondDto> getBonds(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "status", defaultValue = "") String status) {
        try {
            return instrumentService.getAllBonds().stream()
                    .filter(bond -> search.isEmpty() || 
                            bond.getTicker().toLowerCase().contains(search.toLowerCase()) ||
                            bond.getName().toLowerCase().contains(search.toLowerCase()))
                    .filter(bond -> status.isEmpty() || bond.getTradingStatus().name().equalsIgnoreCase(status))
                    .skip(page * size)
                    .limit(size)
                    .map(BondDto::from)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting bonds", e);
            throw new RuntimeException("Error getting bonds: " + e.getMessage(), e);
        }
    }

    @GetMapping("/etfs")
    public List<EtfDto> getEtfs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "status", defaultValue = "") String status) {
        try {
            return instrumentService.getAllEtfs().stream()
                    .filter(etf -> search.isEmpty() || 
                            etf.getTicker().toLowerCase().contains(search.toLowerCase()) ||
                            etf.getName().toLowerCase().contains(search.toLowerCase()))
                    .filter(etf -> status.isEmpty() || etf.getTradingStatus().name().equalsIgnoreCase(status))
                    .skip(page * size)
                    .limit(size)
                    .map(EtfDto::from)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting ETFs", e);
            throw new RuntimeException("Error getting ETFs: " + e.getMessage(), e);
        }
    }

    @GetMapping("/currencies")
    public List<ShareDto> getCurrencies(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "search", defaultValue = "") String search) {
        try {
            // Пока возвращаем пустой список для валют, так как ShareDto не подходит для Currency
            return List.of();
        } catch (Exception e) {
            log.error("Error getting currencies", e);
            throw new RuntimeException("Error getting currencies: " + e.getMessage(), e);
        }
    }

    @GetMapping("/trading-available")
    public List<ShareDto> getTradingAvailable(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "search", defaultValue = "") String search) {
        try {
            return instrumentService.getAllShares().stream()
                    .filter(share -> "SECURITY_TRADING_STATUS_NORMAL_TRADING".equals(share.getTradingStatus().name()))
                    .filter(share -> search.isEmpty() || 
                            share.getTicker().toLowerCase().contains(search.toLowerCase()) ||
                            share.getName().toLowerCase().contains(search.toLowerCase()))
                    .skip(page * size)
                    .limit(size)
                    .map(ShareDto::from)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting trading available instruments", e);
            throw new RuntimeException("Error getting trading available instruments: " + e.getMessage(), e);
        }
    }

    @GetMapping("/tradable/shares")
    public ResponseEntity<?> getTradableShares() {
        try {
            log.info("Поиск акций, доступных для торговли");
            List<Share> shares = instrumentService.getTradableShares();
            List<ShareDto> dtos = shares.stream()
                    .map(ShareDto::from)
                    .collect(Collectors.toList());
            log.info("Найдено {} акций, доступных для торговли", dtos.size());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Ошибка при поиске доступных для торговли акций", e);
            return ResponseEntity.internalServerError()
                    .body("Ошибка при поиске доступных для торговли акций: " + e.getMessage());
        }
    }

    @GetMapping("/tradable/bonds")
    public ResponseEntity<?> getTradableBonds() {
        try {
            log.info("Поиск облигаций, доступных для торговли");
            List<Bond> bonds = instrumentService.getTradableBonds();
            List<BondDto> dtos = bonds.stream()
                    .map(BondDto::from)
                    .collect(Collectors.toList());
            log.info("Найдено {} облигаций, доступных для торговли", dtos.size());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Ошибка при поиске доступных для торговли облигаций", e);
            return ResponseEntity.internalServerError()
                    .body("Ошибка при поиске доступных для торговли облигаций: " + e.getMessage());
        }
    }

    @GetMapping("/tradable/etfs")
    public ResponseEntity<?> getTradableEtfs() {
        try {
            log.info("Поиск ETF, доступных для торговли");
            List<Etf> etfs = instrumentService.getTradableEtfs();
            List<EtfDto> dtos = etfs.stream()
                    .map(EtfDto::from)
                    .collect(Collectors.toList());
            log.info("Найдено {} ETF, доступных для торговли", dtos.size());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Ошибка при поиске доступных для торговли ETF", e);
            return ResponseEntity.internalServerError()
                    .body("Ошибка при поиске доступных для торговли ETF: " + e.getMessage());
        }
    }

    @GetMapping("/count")
    public InstrumentsCountDto getInstrumentsCount(
            @RequestParam(name = "type") String type,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "status", defaultValue = "") String status) {
        try {
            int count = 0;
            switch (type) {
                case "shares":
                    count = (int) instrumentService.getAllShares().stream()
                            .filter(share -> search.isEmpty() || 
                                    share.getTicker().toLowerCase().contains(search.toLowerCase()) ||
                                    share.getName().toLowerCase().contains(search.toLowerCase()))
                            .filter(share -> status.isEmpty() || share.getTradingStatus().name().equalsIgnoreCase(status))
                            .count();
                    break;
                case "bonds":
                    count = (int) instrumentService.getAllBonds().stream()
                            .filter(bond -> search.isEmpty() || 
                                    bond.getTicker().toLowerCase().contains(search.toLowerCase()) ||
                                    bond.getName().toLowerCase().contains(search.toLowerCase()))
                            .filter(bond -> status.isEmpty() || bond.getTradingStatus().name().equalsIgnoreCase(status))
                            .count();
                    break;
                case "etfs":
                    count = (int) instrumentService.getAllEtfs().stream()
                            .filter(etf -> search.isEmpty() || 
                                    etf.getTicker().toLowerCase().contains(search.toLowerCase()) ||
                                    etf.getName().toLowerCase().contains(search.toLowerCase()))
                            .filter(etf -> status.isEmpty() || etf.getTradingStatus().name().equalsIgnoreCase(status))
                            .count();
                    break;
                case "currencies":
                    count = instrumentService.getAllCurrencies().size();
                    break;
                case "trading-available":
                    count = (int) instrumentService.getAllShares().stream()
                            .filter(share -> "SECURITY_TRADING_STATUS_NORMAL_TRADING".equals(share.getTradingStatus().name()))
                            .count();
                    break;
            }
            return new InstrumentsCountDto(type, count);
        } catch (Exception e) {
            log.error("Error getting instruments count", e);
            throw new RuntimeException("Error getting instruments count: " + e.getMessage(), e);
        }
    }

    /**
     * Поиск инструмента по FIGI среди всех типов инструментов
     */
    @GetMapping("/search/{figi}")
    public ResponseEntity<?> findInstrumentByFigi(@PathVariable("figi") String figi) {
        try {
            // Поиск среди акций
            java.util.Optional<ru.tinkoff.piapi.contract.v1.Share> share = instrumentService.getAllShares().stream()
                    .filter(s -> figi.equals(s.getFigi()))
                    .findFirst();
            if (share.isPresent()) {
                return ResponseEntity.ok(ShareDto.from(share.get()));
            }

            // Поиск среди облигаций
            java.util.Optional<ru.tinkoff.piapi.contract.v1.Bond> bond = instrumentService.getAllBonds().stream()
                    .filter(b -> figi.equals(b.getFigi()))
                    .findFirst();
            if (bond.isPresent()) {
                return ResponseEntity.ok(BondDto.from(bond.get()));
            }

            // Поиск среди ETF
            java.util.Optional<ru.tinkoff.piapi.contract.v1.Etf> etf = instrumentService.getAllEtfs().stream()
                    .filter(e -> figi.equals(e.getFigi()))
                    .findFirst();
            if (etf.isPresent()) {
                return ResponseEntity.ok(EtfDto.from(etf.get()));
            }

            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error finding instrument by FIGI: " + figi, e);
            return ResponseEntity.status(500).body("Error finding instrument: " + e.getMessage());
        }
    }
} 