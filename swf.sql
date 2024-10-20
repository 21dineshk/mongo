WITH RankedData AS (
    SELECT 
        HOUSEHOLD_ID,
        ORDERED_BASE_PRODUCT_NBR,
        ORDERED_UPC_ID,
        SUBSTITUTED_BASE_PRODUCT_NBR,
        SUBSTITUTED_UPC_ID,
        ROW_NUMBER() OVER (PARTITION BY HOUSEHOLD_ID, ORDERED_BASE_PRODUCT_NBR ORDER BY SUBSTITUTED_UPC_ID) AS RN
    FROM [dbo].[PS_CUSTOMER_SELECTED_SUBSTITUTION_RANK]
    WHERE HOUSEHOLD_ID = '758011977311'
    AND ORDERED_BASE_PRODUCT_NBR = '960100459'
)
UPDATE [dbo].[PS_CUSTOMER_SELECTED_SUBSTITUTION_RANK]
SET RN = RankedData.RN
FROM [dbo].[PS_CUSTOMER_SELECTED_SUBSTITUTION_RANK]
JOIN RankedData
ON [PS_CUSTOMER_SELECTED_SUBSTITUTION_RANK].HOUSEHOLD_ID = RankedData.HOUSEHOLD_ID
AND [PS_CUSTOMER_SELECTED_SUBSTITUTION_RANK].ORDERED_BASE_PRODUCT_NBR = RankedData.ORDERED_BASE_PRODUCT_NBR
AND [PS_CUSTOMER_SELECTED_SUBSTITUTION_RANK].ORDERED_UPC_ID = RankedData.ORDERED_UPC_ID;



WITH recent_segments AS (
    SELECT 
        household_id,
        aiq_segment_nm,
        CMP_NAME,
        CMP_ID,
        EXPORT_TS,
        ROW_NUMBER() OVER (PARTITION BY household_id ORDER BY EXPORT_TS DESC) AS row_num
    FROM 
        gcp-abs-udco-bqvw-prod-prj-01.udco_ds_cust.RETAIL_CUSTOMER_BACKFEED_ACTIVATION
    WHERE 
        DW_CURRENT_VERSION_IND = TRUE 
        AND CMP_NAME = 'PZN_THANKSGIVING_2024'
),
persona_segments AS (
    SELECT 
        household_id,
        aiq_segment_nm AS persona,
        ROW_NUMBER() OVER (PARTITION BY household_id ORDER BY EXPORT_TS DESC) AS row_num_persona
    FROM 
        recent_segments
    WHERE 
        aiq_segment_nm LIKE '%PERSONA%'
),
buyer_segments AS (
    SELECT 
        household_id,
        aiq_segment_nm AS buyerSegment,
        ROW_NUMBER() OVER (PARTITION BY household_id ORDER BY EXPORT_TS DESC) AS row_num_buyerSegment
    FROM 
        recent_segments
    WHERE 
        aiq_segment_nm NOT LIKE '%PERSONA%'
)
SELECT 
    p.household_id,
    p.persona,
    b.buyerSegment
FROM 
    persona_segments p
FULL OUTER JOIN 
    buyer_segments b
ON 
    p.household_id = b.household_id
WHERE 
    (p.row_num_persona = 1 OR p.row_num_persona IS NULL) AND
    (b.row_num_buyerSegment = 1 OR b.row_num_buyerSegment IS NULL)
ORDER BY 
    household_id;


persona_list = [
    'PZN_THANKSGIVING_2024_PERSONA_5',
    'PZN_THANKSGIVING_2024_PERSONA_4',
    'PZN_THANKSGIVING_2024_PERSONA_3',
    'PZN_THANKSGIVING_2024_PERSONA_2',
    'PZN_THANKSGIVING_2024_PERSONA_1',
    'PZN_THANKSGIVING_2024_NO_PERSONA'
]

buyer_segment_list = [
    'PZN_THANKSGIVING_2024_NO_TURKEY',
    'PZN_THANKSGIVING_2024_TURKEY',
    'PZN_THANKSGIVING_2024_VEGETARIAN',
    'PZN_THANKSGIVING_2024_MEAT_SEAFOOD',
    'PZN_THANKSGIVING_2024_SEAFOOD'
]

persona_str = ', '.join([f"'{segment}'" for segment in persona_list])
buyer_segment_str = ', '.join([f"'{segment}'" for segment in buyer_segment_list])

# SQL query with dynamic lists
query = f"""
WITH segment_data AS (
    SELECT 
        household_id,
        ARRAY_AGG(aiq_segment_nm ORDER BY EXPORT_TS DESC) AS segments
    FROM 
        gcp-abs-udco-bqvw-prod-prj-01.udco_ds_cust.RETAIL_CUSTOMER_BACKFEED_ACTIVATION
    WHERE 
        DW_CURRENT_VERSION_IND = TRUE 
        AND CMP_NAME = 'PZN_THANKSGIVING_2024'
    GROUP BY 
        household_id
)
SELECT
    household_id,
    (SELECT segment FROM UNNEST(segments) AS segment 
        WHERE segment IN ({persona_str})
        LIMIT 1) AS persona,
    (SELECT segment FROM UNNEST(segments) AS segment 
        WHERE segment IN ({buyer_segment_str})
        LIMIT 1) AS buyerSegment
FROM 
    segment_data;
"""




WITH ranked_categories AS (
    SELECT
        HOUSEHOLD_ID,
        SMIC_DEPT_NM,
        MIN(CATEGORY_RANK) AS MIN_CATEGORY_RANK
    FROM
        YOUR_TABLE_NAME
    WHERE
        SMIC_DEPT_NM != 'Meat & Seafood'
    GROUP BY
        HOUSEHOLD_ID,
        SMIC_DEPT_NM
),
sorted_categories AS (
    SELECT
        HOUSEHOLD_ID,
        SMIC_DEPT_NM,
        MIN_CATEGORY_RANK,
        ROW_NUMBER() OVER (PARTITION BY HOUSEHOLD_ID ORDER BY MIN_CATEGORY_RANK) AS CATEGORY_RANK
    FROM
        ranked_categories
),
departments AS (
    SELECT
        HOUSEHOLD_ID,
        SMIC_DEPT_NM,
        DIGITAL_DEPT_NM
    FROM
        YOUR_TABLE_NAME
    WHERE
        SMIC_DEPT_NM != 'Meat & Seafood'
)
SELECT
    s.HOUSEHOLD_ID,
    s.SMIC_DEPT_NM AS category,
    s.CATEGORY_RANK AS rank,
    ARRAY_AGG(d.DIGITAL_DEPT_NM) AS departments
FROM
    sorted_categories s
JOIN
    departments d
ON
    s.HOUSEHOLD_ID = d.HOUSEHOLD_ID
    AND s.SMIC_DEPT_NM = d.SMIC_DEPT_NM
GROUP BY
    s.HOUSEHOLD_ID,
    s.SMIC_DEPT_NM,
    s.CATEGORY_RANK
ORDER BY
    s.HOUSEHOLD_ID,
    s.CATEGORY_RANK;
