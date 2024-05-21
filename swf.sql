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
