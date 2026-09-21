## Purpose

The technique an exam on a particular order was run by: how it is chosen from what its template offers, why the template has to be settled first, when the template's default is stamped onto the exam, and what the order and its report say about it afterwards.

## ADDED Requirements

### Requirement: The method of an exam is chosen by name, from its template

Setting the method of an exam on an order SHALL accept a method **name**, not an identifier the caller had to look up. The name SHALL be resolved against the methods of that exam's template, ignoring accents, spacing and capitalisation: a name the template already holds SHALL link the exam to it, and a name it does not SHALL be added to the template and the exam linked to the new method. No separate request SHALL be needed to register the method first.

Setting the method SHALL also mark it as that template's default, per the `test-method-catalog` capability.

An empty or blank name SHALL leave the exam with no method, SHALL NOT add anything to the template and SHALL NOT change the template's default — declining to state the technique is not a statement about what the usual technique is.

#### Scenario: Choosing a method the template offers

- **WHEN** an exam is given the method "ELISA" and its template already holds it
- **THEN** the exam names that method, nothing is added to the template, and it becomes the template's default

#### Scenario: Writing a method the template does not have

- **WHEN** an exam is given a method name its template does not hold
- **THEN** the method is added to that template, the exam names it, and it becomes the template's default

#### Scenario: Clearing the method

- **WHEN** an exam that named a method is given an empty name
- **THEN** the exam names no method, the method stays on the template, and the template's default is unchanged

### Requirement: An exam without a template cannot be given a method

Setting the method of an exam whose template has not been assigned yet SHALL be refused, with a business-rule error saying the exam's template has to be settled first — the methods on offer are the template's, so until there is a template there is nothing to choose from and nowhere to put a new one. The refusal SHALL change nothing.

An exam with no template SHALL report no method, and SHALL NOT be treated as an error in its own right: it is a transient state of an exam whose template is about to be chosen.

#### Scenario: Setting the method too early

- **WHEN** the method is set on an exam whose template has not been assigned
- **THEN** the request is refused with a business-rule error naming what is missing, and the exam is unchanged

#### Scenario: Reading such an exam

- **WHEN** an exam with no template is read
- **THEN** it reports no method and no error

### Requirement: Assigning the template stamps its default onto the exam

Assigning a template to an exam on an order SHALL give the exam that template's default method, when the template has one and the exam does not already name a method. An exam that already names one SHALL keep it: a template assignment SHALL never overwrite a method somebody chose.

This is the point at which a new exam acquires its method, so that the technician opening the order finds it already stated rather than having to set it every time.

Changing a template's default afterwards SHALL NOT reach any exam already stamped. What an order says was used stays what it said, on the order and on its report.

#### Scenario: A new exam of a template with a default

- **WHEN** a template whose default is "ELISA" is assigned to an exam that has no method
- **THEN** the exam names "ELISA"

#### Scenario: A template with no default

- **WHEN** a template with no default is assigned to an exam
- **THEN** the exam names no method, and the assignment succeeds

#### Scenario: An exam that already has a method

- **WHEN** a template is assigned to an exam that already names a method
- **THEN** the exam keeps the method it had

#### Scenario: The default changes later

- **WHEN** a template's default changes after exams of it were created
- **THEN** those exams still name what they named, and their reports are unchanged

### Requirement: An exam reports its method by name, and identifies it

An exam read from the API SHALL report its method as a name, in the same field and the same shape as before methods lived on templates, so that a caller written against the previous behaviour keeps working. The name reported SHALL be the template's current name for that method, which is what makes a correction reach the orders already recorded.

An exam SHALL additionally report a read-only identifier for its method, so that a caller can tell which of the template's methods it is without matching on text. That identifier SHALL be ignored if it is sent on a write: the name is what is written.

The printed report SHALL keep stating the method exactly as it does today — when there is one, in the place it already appears — and SHALL keep omitting it when there is none.

#### Scenario: Reading an exam with a method

- **WHEN** an exam naming a method is read
- **THEN** it reports the method's current name, and its identifier alongside

#### Scenario: A caller written before the change

- **WHEN** a caller that only knows the method-name field sets and reads the method of an exam whose template is assigned
- **THEN** it behaves as it did before, including adding the method to the template when the name is new

#### Scenario: The report

- **WHEN** the report of an order is produced
- **THEN** each exam states its method where it always did, and states none where there is none
