from ocr_worker.app.main import parse_tesseract_tsv


def test_parse_tesseract_tsv_filters_blank_and_low_confidence_rows():
    output = "\n".join(
        [
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext",
            "5\t1\t1\t1\t1\t1\t10\t20\t30\t12\t92.3\tDRAWING",
            "5\t1\t1\t1\t1\t2\t50\t20\t20\t12\t41.0\tLOW",
            "5\t1\t1\t1\t1\t3\t80\t20\t20\t12\t95.0\t",
        ]
    )

    regions = parse_tesseract_tsv(output, 0.5, "eng")

    assert regions == [
        {
            "text": "DRAWING",
            "confidence": 0.923,
            "xyxy": [10.0, 20.0, 40.0, 32.0],
            "language": "eng",
        }
    ]
