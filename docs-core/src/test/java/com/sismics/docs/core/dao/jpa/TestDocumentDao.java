package com.sismics.docs.core.dao.jpa;

import com.sismics.docs.BaseTransactionalTest;
import com.sismics.docs.core.dao.DocumentDao;
import com.sismics.docs.core.model.jpa.Document;
import com.sismics.docs.core.util.TransactionUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;

public class TestDocumentDao extends BaseTransactionalTest {

    @Test
    public void testDocumentDao() throws Exception {
        // Create a document
        DocumentDao documentDao = new DocumentDao();
        Document document = new Document();
        document.setUserId("test-user");
        document.setFileId("test-file");
        document.setLanguage("eng");
        document.setTitle("Test Document");
        document.setDescription("This is a test document");
        document.setSubject("Test Subject");
        document.setIdentifier("TEST-001");
        document.setPublisher("Test Publisher");
        document.setFormat("PDF");
        document.setSource("Test Source");
        document.setType("Test Type");
        document.setCoverage("Global");
        document.setRights("All rights reserved");
        document.setCreateDate(new Date());
        document.setUpdateDate(new Date());

        // Create the document using the correct method with two parameters
        String documentId = documentDao.create(document, "test-user");
        TransactionUtil.commit();

        // Retrieve the document by its ID
        document = documentDao.getById(documentId);
        Assert.assertNotNull(document);
        Assert.assertEquals("Test Document", document.getTitle());

        // Update the document
        document.setTitle("Updated Test Document");
        documentDao.update(document, "test-user");
        TransactionUtil.commit();

        // Retrieve the updated document
        Document updatedDocument = documentDao.getById(documentId);
        Assert.assertNotNull(updatedDocument);
        Assert.assertEquals("Updated Test Document", updatedDocument.getTitle());

        // Delete the created document
        documentDao.delete(documentId, "test-user");
        TransactionUtil.commit();

        // Ensure the document is deleted
        Assert.assertNull(documentDao.getById(documentId));
    }
}