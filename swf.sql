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
